# mesos-crate

At the moment only compatible with ubuntu 14.04 64bit!!

## Installation

you need to allow ports:

aws ec2 authorize-security-group-ingress --group-name jclouds#mesos-master --protocol tcp --port 5050 --cidr <your ip range>
aws ec2 authorize-security-group-ingress --group-name jclouds#mesos-master --protocol tcp --port 8080 --cidr <your ip range>

ip range is in cidr format for example if you just want to authorize your IP address get it from whatsmyip.org and you xx.xx.xx.xx/32
you can authorize xx.xx.xx.** whild card on last digits with xx.xx.xx.xx/24
you can also allow the whole internet access with 0.0.0.0/0 but this is dangerous.

## Usage

FIXME: explanation

    $ java -jar mesos-crate-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
