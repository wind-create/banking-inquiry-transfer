INSERT INTO customers (
    customer_id,
    full_name,
    email,
    created_by,
    updated_by
)
VALUES
    ('CUST001', 'Willi', 'customer1@gmail.com', 'SYSTEM', 'SYSTEM'),
    ('CUST002', 'Nardo', 'customer2@gmail.com', 'SYSTEM', 'SYSTEM')
ON CONFLICT (customer_id) DO NOTHING;


INSERT INTO accounts (
    account_number,
    customer_id,
    balance,
    currency,
    status,
    version,
    created_by,
    updated_by
)
SELECT
    seed.account_number,
    c.id,
    seed.balance,
    'IDR',
    'ACTIVE',
    0,
    'SYSTEM',
    'SYSTEM'
FROM (
    VALUES
        ('1000012345', 'CUST001', 15000000.00),
        ('1000012346', 'CUST001',  5000000.00),
        ('2000012345', 'CUST002',  8000000.00)
) AS seed(account_number, customer_id, balance)
JOIN customers c
    ON c.customer_id = seed.customer_id
ON CONFLICT (account_number) DO NOTHING;