# Cool side project

- [x] need to work on bitcoind framework
- [x] need to put server config files into a repo
- [x] added markdown processing for form input, make your own order form. TODO- test
- [ ] work on creating cart flow, update | checkout -> enter address -> confirm -> complete
- [.5] need to start on admin functionality

#TODO: - custom listing ideas
listings should have a order completion form, this will be part
of the checkout process.
dump a raw blob of unstructured data.


sudo apt-get install bitcoind

## Setup

There are several scripts that will setup postgres, and couchbase for first time installation.

[1]: to install couchbase run `./scripts/install-couchbase.sh`
[2]: to install postgres run `./scripts/install-postgres.sh`
[3]: to create the db run `./scripts/db-run-up.sh`

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
•	Verified Vendor fee.
•	Permalinks for vendors, /user/alias done
•	http://directory4iisquf.onion add verified vendor directory, for pgp keys. done
•	Contract support – send message - in process
•	Allow Vendors to post to followers
•	Add latest posts to user page.
•	Add an option to encrypt all messages to a user.
 
