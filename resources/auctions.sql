CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE auctions
(
    timestamp timestamp,
    item_id int,
    count int,
    min float,
    first_quartile float,
    median float,
    third_quartile float,
    max float,
    realm varchar(64)
);


SELECT create_hypertable('auctions', 'timestamp');
