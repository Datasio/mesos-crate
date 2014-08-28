(ns pallet.crate.mesos
  "Crate for mesos"
  (:require
   [clojure.tools.logging :refer [debugf]]
   [pallet.actions :refer [package-source package-manager packages exec-checked-script]]
   [pallet.crate         :refer [defplan nodes-with-role group-name target-node target]]
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
  (let [ip-range (str (primary-ip (target-node)) "/32")
        group (get-group-name :zookeeper)
        protocol :tcp]
    (doseq [port [2181]]
      (allow-port-rule ip-range port protocol group)))
  (let [ip-range (str (private-ip (target-node)) "/32")
        group (get-group-name :master)
        protocol :tcp
        port 5050]
    (allow-port-rule ip-range port protocol group)))

(defplan allow-master-access-to-slave []
  (let [ip-range (str (get-public-ip :master) "/32")
        group (get-group-name :slave)
        protocol :tcp
        port 5051]
      (allow-port-rule ip-range port protocol group)))


(defplan doseq-service [services action]
  (doseq [service-name services]
    (exec-checked-script "Service Control"
                         (do ("service" ~service-name ~(name action))
                           ("exit" "0")))))

(defplan stop-services [services]
  (doseq-service services :stop))

(defplan disable-services [services]
  (doseq [service-name services]
    (exec-checked-script "Disable service"
                         (~(format "echo manual > /etc/init/%s.override" service-name)))))

(defplan restart-services [services]
  (doseq-service services :restart))

(defplan set-zookeeper-ip []
  (exec-checked-script "Set zookeeper ip" (~(format "echo zk://%s:2181/mesos > /etc/mesos/zk" (get-public-ip :zookeeper)))))

(defplan set-hostname [file]
  (exec-checked-script "Set hostname" (~(format "echo %s > %s" (primary-ip (target-node)) file))))

(defplan set-master-hostname []
  (set-hostname "/etc/mesos-master/hostname"))

(defplan set-slave-hostname []
  (set-hostname "/etc/mesos-slave/hostname"))

(defplan mesos-install []
  (package-source "Mesos" :apt {:url "http://repos.mesosphere.io/ubuntu" :key-server "keyserver.ubuntu.com" :key-id "E56151BF"})
  (package-manager :update)
  (packages :apt ["mesos" "marathon" "unzip"]))

(defplan mesos-master-config []
  (stop-services ["mesos-slave"])
  (disable-services ["mesos-slave"])
  (set-master-hostname)
  (restart-services ["mesos-master" "marathon" "zookeeper"]))

(defplan mesos-slave-config []
  (stop-services ["zookeeper" "mesos-master"])
  (disable-services ["zookeeper" "mesos-master"])
  (set-slave-hostname)
  (allow-slave-access-to-zookeeper-and-master)
  (allow-master-access-to-slave)
  (set-zookeeper-ip)
  (restart-services ["mesos-slave"]))
