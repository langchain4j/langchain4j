INSERT INTO customers (customer_id, first_name, last_name, email)
VALUES (1, 'John', 'Doe', 'john.doe@example.com'),
       (2, 'Jane', 'Smith', 'jane.smith@example.com'),
       (3, 'Alice', 'Johnson', 'alice.johnson@example.com'),
       (4, 'Bob', 'Williams', 'bob.williams@example.com'),
       (5, 'Carol', 'Brown', 'carol.brown@example.com');

INSERT INTO products (product_id, product_name, price)
VALUES (10, 'Notebook', 12.99),
       (20, 'Pen', 1.50),
       (30, 'Desk Lamp', 23.99),
       (40, 'Backpack', 49.99),
       (50, 'Stapler', 7.99);

INSERT INTO orders (order_id, customer_id, product_id, quantity, order_date)
VALUES (100, 1, 10, 2, '2024-04-20'),
       (200, 2, 20, 5, '2024-04-21'),
       (300, 3, 10, 1, '2024-04-22'),
       (400, 4, 30, 1, '2024-04-23'),
       (500, 5, 40, 1, '2024-04-24'),
       (600, 1, 50, 3, '2024-04-25'),
       (700, 2, 10, 2, '2024-04-26'),
       (800, 3, 40, 1, '2024-04-27'),
       (900, 4, 20, 10, '2024-04-28'),
       (10000, 5, 30, 2, '2024-04-29');
