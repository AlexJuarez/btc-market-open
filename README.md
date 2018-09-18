#TODO: - custom listing ideas

## Setup

There are several scripts that will setup postgres, and couchbase for first time installation.

[1]: to install couchbase run `./scripts/install-couchbase.sh`
[2]: to install postgres run `./scripts/install-postgres.sh`
[3]: to create the db run `./scripts/db-run-up.sh`

## Start

To run a dev webserver `./start.sh`

### install bitcoin client
sudo apt-get install bitcoind

### Reseting the postgres db

To reset the postgres db run `./scripts/db-run-down.sh`

### Check dependencies

To check if all dependencies are up to date run lein ancient - https://github.com/xsc/lein-ancient

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed. You need [Postgresql][2] 9.1.10
and you will need [couchbase][3] 2.2.0

[1]: https://github.com/technomancy/leiningen
[2]: https://help.ubuntu.com/community/PostgreSQL
[3]: http://www.couchbase.com/docs//couchbase-manual-2.0/couchbase-getting-started-install-ubuntu.html

## Running

To check on the application stats visit http://ubuntu:8091/index.html

To start a web server for the application, run:

    lein run

## License

Copyright © 2013 FIXME

Future Enhancements
