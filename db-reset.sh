set -e

psql -c "drop database whitecity;"
psql -c "create database whitecity;"
psql -c "grant all privileges on database whitecity to devil;"
