BEGIN;

INSERT INTO products (id, name, category, price, stock, active, created_at) VALUES
    (1, 'Notebook Lenovo ThinkPad E14', 'Computacion', 749990.00, 8, true, CURRENT_TIMESTAMP),
    (2, 'Monitor Samsung Essential S3 24', 'Monitores', 109990.00, 15, true, CURRENT_TIMESTAMP),
    (3, 'Teclado Logitech K120', 'Accesorios', 14990.00, 30, true, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    price = EXCLUDED.price,
    stock = EXCLUDED.stock,
    active = EXCLUDED.active;

INSERT INTO customers (id, full_name, email, phone) VALUES
    (1, 'Camila Rojas', 'camila.rojas@cliente.test', '+56 9 6123 4587'),
    (2, 'Diego Morales', 'diego.morales@cliente.test', '+56 9 7345 2198'),
    (3, 'Valentina Soto', 'valentina.soto@cliente.test', '+56 9 8456 3071')
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone;

INSERT INTO suppliers (id, company_name, contact_email, phone) VALUES
    (1, 'TecnoSuministros SpA', 'ventas@tecnosuministros.test', '+56 2 2450 1180'),
    (2, 'Distribuidora Andes Ltda', 'contacto@distribuidoraandes.test', '+56 2 2680 2240')
ON CONFLICT (id) DO UPDATE SET
    company_name = EXCLUDED.company_name,
    contact_email = EXCLUDED.contact_email,
    phone = EXCLUDED.phone;

INSERT INTO notifications (id, recipient, message, read_status, created_at) VALUES
    (1, 'inventario', 'Notebook Lenovo ThinkPad E14 con stock bajo: 8 unidades.', false, CURRENT_TIMESTAMP),
    (2, 'operaciones', 'Catalogo de proveedores actualizado correctamente.', true, CURRENT_TIMESTAMP),
    (3, 'ventas', 'Precio del monitor Samsung actualizado para la campana mensual.', false, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    recipient = EXCLUDED.recipient,
    message = EXCLUDED.message,
    read_status = EXCLUDED.read_status;

SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT MAX(id) FROM products), true);
SELECT setval(pg_get_serial_sequence('customers', 'id'), (SELECT MAX(id) FROM customers), true);
SELECT setval(pg_get_serial_sequence('suppliers', 'id'), (SELECT MAX(id) FROM suppliers), true);
SELECT setval(pg_get_serial_sequence('notifications', 'id'), (SELECT MAX(id) FROM notifications), true);

COMMIT;
