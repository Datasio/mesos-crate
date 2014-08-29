(ns pallet.crate.mesos
  "Crate for mesos"
  (:require
   [digest]
   [clojure.tools.logging :refer [debugf]]
   [pallet.actions :refer [package-source package-manager packages exec-checked-script]]
   [pallet.crate         :refer [defplan defmulti-plan defmethod-plan nodes-with-role group-name target-node target]]
   [pallet.crate.service :refer [service]]
   [pallet.node :refer [running? primary-ip private-ip compute-service]]
   [com.palletops.jclouds.ec2.security-group2 :as sg2]
   [com.palletops.jclouds.ec2.core :refer [get-region]]
   [clojure.java.io :refer [as-file]]))

(defplan get-public-ip
  [role]
  (->> (nodes-with-role role)
       first
       primary-ip))

(defplan get-private-ip
  [role]
  (->> (nodes-with-role role)
       first
       private-ip))


(defplan get-group-name [role]
   (->> (nodes-with-role role)
         first
         pallet.node/group-name
         name
        (str "jclouds#")))

(defplan allow-port-rule [ip-range port protocol group]
  (let [compute (compute-service (:node (target)))
        region (get-region (:node (target)))]
  (try
    (sg2/authorize compute group port :protocol protocol :ip-range ip-range :region region)
    (catch IllegalStateException e
      (if (re-find #"has already been authorized" (.getMessage e))
        (debugf "Already authorized")
        (throw e))))))

(defplan allow-slave-access-to-zookeeper-and-master []
  (let [ip-range (str (private-ip (target-node)) "/32")
        group (get-group-name :zookeeper)
        protocol :tcp
        port 2181]
    (allow-port-rule ip-range port protocol group))
  (let [ip-range (str (private-ip (target-node)) "/32")
        group (get-group-name :master)
        protocol :tcp
        port 5050]
    (allow-port-rule ip-range port protocol group)))

(defplan allow-master-access-to-slave []
  (let [ip-range (str (get-private-ip :master) "/32")
        group (get-group-name :slave)
        protocol :tcp
        port 5051]
      (allow-port-rule ip-range port protocol group)))

(defplan doseq-service [services action]
  (doseq [service-name services]
    (exec-checked-script "Service Control"
                         (do ("service" ~service-name ~(name action))
                           ("exit" "0")))))

(defplan disable-services [services]
  (doseq-service services :stop)
  (doseq [service-name services]
    (exec-checked-script "Disable service"
                         (~(format "echo manual > /etc/init/%s.override" service-name)))))

(defplan restart-services [services]
  (doseq-service services :restart))

(defplan config-slave []
  (let [target-public-ip  (primary-ip (target-node))
        zookeeper-ip (get-private-ip :zookeeper)]
    (exec-checked-script "Set hostname" ("echo" ~target-primary-ip  ">" "/etc/mesos-slave/hostname"))
    (exec-checked-script "Set zookeeper ip" ("echo" ~(format "zk://%s:2181/mesos" zookeeper-ip)  ">" "/etc/mesos/zk"))))

(defplan config-master []
    (exec-checked-script "Set mesos hostname" ("echo" ~(primary-ip (target-node)) ">" "/etc/mesos-master/hostname"))
    (remote-file "/etc/haproxy/haproxy-base.cfg"
                 :md5 (digest/md5 "resources/haproxy-base.cfg")
                 :owner "haproxy"
                 :group "haproxy")
    (remote-file "/usr/bin/haproxy-marathon-bridge-mod"
                 :md5 (digest/md5 "resources/haproxy-marathon-bridge-mod")
                 :mode "755")

    (exec-checked-script "install haproxy-marathon-bridge-mod"
                      ("haproxy-marathon-bridge-mod install_haproxy_system 127.0.0.1:8080")))


(defplan add-slaves-to-hosts []
    (exec-checked-script "backup hosts" ("cp /etc/hosts /etc/hosts-base"))
    (doseq [node (nodes-with-role :slave)]
      (echo ~(private-ip node) " " ~(public-ip node) " > /etc/hosts")))


(defplan add-mesos-repo []
  (package-source "Mesos" :apt {:url "http://repos.mesosphere.io/ubuntu" :key-server "keyserver.ubuntu.com" :key-id "E56151BF"})
  (package-manager :update)
  (packages :apt ["mesos" "marathon" "chronos" "unzip"]))

(defplan mesos-master-install []
  (add-mesos-repo)
  (packages :apt ["mesos" "marathon" "chronos" "unzip" "python-pip"])
  (exec-checked-script "Install aws cli" ("pip install awscli"))
  (disable-services ["mesos-slave"])
  (config-master)
  (restart-services ["mesos-master" "marathon" "zookeeper"]))

(defplan mesos-slave-install []
  (add-mesos-repo)
  (packages :apt ["mesos" "unzip"])
  (disable-services ["zookeeper" "mesos-master"])
  (config-slave)
  (allow-slave-access-to-zookeeper-and-master)
  (allow-master-access-to-slave)
  (add-to-master-hosts-file)
  (restart-services ["mesos-slave"]))
