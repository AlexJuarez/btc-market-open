set -x
set -e

DATA_SIZE=512
INDEX_SIZE=256

/opt/couchbase/bin/couchbase-cli cluster-init -c 127.0.0.1:8091 --cluster-init-username=admin --cluster-init-password=whitecity \
--cluster-init-ramsize=3000 --cluster-index-ramsize=$INDEX_SIZE --cluster-ramsize=$DATA_SIZE
/opt/couchbase/bin/couchbase-cli node-init -c 127.0.0.1:8091 -u admin -p whitecity \
--node-init-data-path=/opt/couchbase/var/lib/couchbase/data --node-init-index-path=/opt/couchbase/var/lib/couchbase/data \
--node-init-hostname=127.0.0.1
/opt/couchbase/bin/couchbase-cli bucket-create -c 127.0.0.1:8091 -u admin -p whitecity \
--bucket=default --bucket-type=couchbase --bucket-ramsize=$DATA_SIZE --bucket-replica=1 --wait
