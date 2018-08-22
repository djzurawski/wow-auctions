CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE auctions
(
    timestamp timestamp,
    realm varchar(64),
    min float,
    max float,
    first_quartile float,
    third_quartile float,
    median float,
    item_id int
 );


SELECT create_hypertable('auctions', 'timestamp');
