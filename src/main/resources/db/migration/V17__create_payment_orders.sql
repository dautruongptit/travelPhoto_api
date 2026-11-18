-- ============ PAYMENT ORDERS (VNPAY) ============
CREATE TABLE IF NOT EXISTS payment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    plan_id VARCHAR(50) NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    storage_gb BIGINT NOT NULL,
    duration_days INT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    vnp_transaction_no VARCHAR(64),
    vnp_bank_code VARCHAR(50),
    vnp_card_type VARCHAR(50),
    vnp_pay_date VARCHAR(20),
    vnp_response_code VARCHAR(20),
    vnp_transaction_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_orders_user ON payment_orders(user_id);
CREATE INDEX idx_payment_orders_order ON payment_orders(order_id);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);
