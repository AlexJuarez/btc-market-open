set -e

psql -c "create user devil with password 'admin';"
psql -c "create database whitecity;"
psql -c "grant all privileges on database whitecity to devil;"
