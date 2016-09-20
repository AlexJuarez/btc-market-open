set -e
set -x

wget --retry-connrefused --waitretry=1 --read-timeout=20 --timeout=15 -t 0 http://packages.couchbase.com/releases/4.1.0-dp/couchbase-server_4.1.0-dp-ubuntu14.04_amd64.deb
sudo dpkg -i ./couchbase-server_4.1.0-dp-ubuntu14.04_amd64.deb
