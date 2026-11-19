CREATE TABLE island_items (
    id BIGINT PRIMARY KEY, name VARCHAR(50) NOT NULL, category VARCHAR(20) NOT NULL,
    price INT NOT NULL, placed_order INT NOT NULL,
    float_x DECIMAL(5,2) NULL, float_y DECIMAL(5,2) NULL,
    display_order INT NOT NULL, active BOOLEAN NOT NULL,
    CONSTRAINT ck_island_item_category CHECK(category IN ('PLANT','PATH','FURNITURE','LIGHT','PROP')),
    CONSTRAINT ck_island_item_price CHECK(price>0),
    CONSTRAINT ck_island_item_float CHECK((float_x IS NULL AND float_y IS NULL) OR (float_x IS NOT NULL AND float_y IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE island_purchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, item_id BIGINT NOT NULL,
    price INT NOT NULL, wool_transaction_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_island_purchase_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_island_purchase_item FOREIGN KEY(item_id) REFERENCES island_items(id),
    CONSTRAINT fk_island_purchase_wool FOREIGN KEY(wool_transaction_id) REFERENCES wool_transactions(id),
    CONSTRAINT uq_island_purchase_user_item UNIQUE(user_id,item_id),
    CONSTRAINT uq_island_purchase_wool UNIQUE(wool_transaction_id),
    CONSTRAINT ck_island_purchase_price CHECK(price>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

INSERT INTO island_items(id,name,category,price,placed_order,float_x,float_y,display_order,active) VALUES
    (1,'올리브 나무','PLANT',12,2,NULL,NULL,1,true),
    (2,'꽃 화분','PLANT',24,7,NULL,NULL,2,true),
    (7,'랜턴','LIGHT',12,11,NULL,NULL,7,true),
    (9,'치즈 선반','FURNITURE',12,5,NULL,NULL,9,true),
    (10,'우편함','PROP',24,8,NULL,NULL,10,true),
    (11,'나룻배','PROP',12,12,65.50,85.00,11,true),
    (12,'러그','PATH',24,1,NULL,NULL,12,true);
