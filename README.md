<h1> VMware has ended active development of this project, this repository will no longer be updated.</h1><br># Overview
Demonstrates the use of "putIfAbsent" for locking.

# Instructions

```
# pull down the submodules
#
git submodule init
git submodule update

# initialize and start a local gemfire cluster
cd local-cluster
export GEMFIRE=path-to-gemfire-install
export JAVA_HOME=path-to-jdk

# start a local cluster
python cluster.py start

# note: you can stop the cluster with python cluster.py stop
# locator = localhost[10000]
# pulseURL = http://localhost:17070/pulse (login with admin/admin)

# build the project
cd ..
mvn package

# run the locker
python locker.py --locator=localhost[10000] --threads=10

# let it run for 10-20 seconds then hit enter to stop
# you will see something like this:
Run Results
===================================================
A: txns=   3836 bal=   184969
B: txns=   3828 bal=   185898
C: txns=   4224 bal=   208558
D: txns=   3879 bal=   190187
E: txns=   3906 bal=   193729

# this is what the balances SHOULD be, now verify with a gfsh query
$GEMFIRE/bin/gfsh -e "connect --locator=localhost[10000]" -e "query --query='select distinct key,value from /data-region.entries order by key'"

# you should see something like this
key | value
--- | ------
A   | 184969
B   | 185898
C   | 208558
D   | 190187
E   | 193729

```
# Notes

- The locking region must be a global scope replicate region.  That type of region
can't be created directly through gfsh which is why this example is using
a cache.xml file.
