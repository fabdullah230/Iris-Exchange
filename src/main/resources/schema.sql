-- Orders table
CREATE TABLE orders (
                        order_id VARCHAR(50) PRIMARY KEY,
                        cl_ord_id VARCHAR(50),
                        instrument_id VARCHAR(20),
                        side VARCHAR(4),
                        quantity DECIMAL(18,8),
                        remaining_quantity DECIMAL(18,8),
                        price DECIMAL(18,8),
                        order_type VARCHAR(10),
                        time_in_force VARCHAR(3),
                        client_id VARCHAR(50),
                        source_ip VARCHAR(50),
                        entry_time TIMESTAMP,
                        last_updated_time TIMESTAMP,
                        status VARCHAR(10),
                        json_data JSONB
);

-- Trades.class table
CREATE TABLE trades (
                        trade_id VARCHAR(50) PRIMARY KEY,
                        instrument_id VARCHAR(20),
                        price DECIMAL(18,8),
                        quantity DECIMAL(18,8),
                        buyer_order_id VARCHAR(50),
                        seller_order_id VARCHAR(50),
                        buyer_cl_ord_id VARCHAR(50),
                        seller_cl_ord_id VARCHAR(50),
                        buyer_client_id VARCHAR(50),
                        seller_client_id VARCHAR(50),
                        trade_time TIMESTAMP,
                        json_data JSONB,
                        FOREIGN KEY (buyer_order_id) REFERENCES orders(order_id),
                        FOREIGN KEY (seller_order_id) REFERENCES orders(order_id)
);

-- Orderbook state table
CREATE TABLE orderbook_state (
                                 id BIGSERIAL PRIMARY KEY,
                                 instrument_id VARCHAR(20),
                                 timestamp TIMESTAMP,
                                 best_bid_price DECIMAL(18,8),
                                 best_bid_quantity DECIMAL(18,8),
                                 second_bid_price DECIMAL(18,8),
                                 second_bid_quantity DECIMAL(18,8),
                                 third_bid_price DECIMAL(18,8),
                                 third_bid_quantity DECIMAL(18,8),
                                 best_ask_price DECIMAL(18,8),
                                 best_ask_quantity DECIMAL(18,8),
                                 second_ask_price DECIMAL(18,8),
                                 second_ask_quantity DECIMAL(18,8),
                                 third_ask_price DECIMAL(18,8),
                                 third_ask_quantity DECIMAL(18,8),
                                 bids_json JSONB,
                                 asks_json JSONB
);

-- Settlement price table
CREATE TABLE settlement_prices (
                                   id BIGSERIAL PRIMARY KEY,
                                   instrument_id VARCHAR(20),
                                   settlement_date DATE,
                                   price DECIMAL(18,8),
                                   closing_trade_volume DECIMAL(18,8),
                                   UNIQUE(instrument_id, settlement_date)
);

-- Reference price table
CREATE TABLE reference_prices (
                                  id BIGSERIAL PRIMARY KEY,
                                  instrument_id VARCHAR(20),
                                  reference_price DECIMAL(18,8),
                                  price_upper_limit DECIMAL(18,8),
                                  price_lower_limit DECIMAL(18,8),
                                  last_updated TIMESTAMP,
                                  sequence_number BIGINT UNIQUE NOT NULL DEFAULT nextval('sequence_number_seq'),

                                  UNIQUE(instrument_id)
);

-- Exchange config table
CREATE TABLE exchange_config (
                                 service_name VARCHAR(50) PRIMARY KEY,
                                 config_json JSONB,
                                 last_updated TIMESTAMP
);

-- Risk management table
CREATE TABLE risk_management (
                                 risk_parameter VARCHAR(50) PRIMARY KEY,
                                 config_json JSONB,
                                 last_updated TIMESTAMP
);

-- FIX participant sessions table
CREATE TABLE fix_participant_sessions (
                                          name VARCHAR(50) PRIMARY KEY,
                                          fix_sender_comp_id VARCHAR(50) UNIQUE,
                                          clearing_account VARCHAR(50),
                                          is_active BOOLEAN DEFAULT true,
                                          is_halted BOOLEAN DEFAULT false,
                                          json_config JSONB,
                                          last_updated TIMESTAMP
);

-- Instruments table
CREATE TABLE instruments (
                             symbol VARCHAR(20) PRIMARY KEY,
                             last_settlement_price DECIMAL(18,8),
                             volume_tick_size DECIMAL(18,8),
                             trading_hours VARCHAR(20),
                             is_active BOOLEAN DEFAULT true,
                             last_updated TIMESTAMP
);

-- FIX messages table
CREATE TABLE fix_messages (
                              id BIGSERIAL PRIMARY KEY,
                              sender_comp_id VARCHAR(50),
                              target_comp_id VARCHAR(50),
                              message_type VARCHAR(2),
                              message_direction VARCHAR(10), -- INBOUND/OUTBOUND
                              raw_message TEXT,
                              parsed_message JSONB,
                              timestamp TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_orders_client_id ON orders(client_id);
CREATE INDEX idx_orders_instrument_id ON orders(instrument_id);
CREATE INDEX idx_trades_instrument_id ON trades(instrument_id);
CREATE INDEX idx_trades_trade_time ON trades(trade_time);
CREATE INDEX idx_orderbook_instrument_time ON orderbook_state(instrument_id, timestamp);
CREATE INDEX idx_fix_messages_timestamp ON fix_messages(timestamp);
CREATE INDEX idx_reference_prices_sequence_number ON reference_prices(sequence_number);

CREATE SEQUENCE sequence_number_seq
    START 1
INCREMENT 1
CACHE 1;