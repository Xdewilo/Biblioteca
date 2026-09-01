-- by Jeremy Posada
-- V2 - Catalogo minimo de ejemplo. Las cuentas no se siembran aqui (las crea DataSeeder)
-- para no versionar ningun hash de contrasena. Los ISBN son reales.

INSERT INTO books (title, author, isbn, publication_year, status, created_at, updated_at, version) VALUES
  ('El nombre de la rosa',   'Umberto Eco',            '9788497592581', 1980, 'DISPONIBLE', NOW(), NOW(), 0),
  ('Rayuela',                'Julio Cortazar',         '9788437604572', 1963, 'DISPONIBLE', NOW(), NOW(), 0),
  ('La casa de los espiritus','Isabel Allende',        '9788401242144', 1982, 'DISPONIBLE', NOW(), NOW(), 0),
  ('Clean Architecture',     'Robert C. Martin',       '9780134494166', 2017, 'DISPONIBLE', NOW(), NOW(), 0),
  ('Refactoring',            'Martin Fowler',          '9780134757599', 2018, 'DISPONIBLE', NOW(), NOW(), 0);

INSERT INTO book_subjects (book_id, subject)
SELECT id, 'Narrativa' FROM books WHERE isbn IN
  ('9788497592581', '9788437604572', '9788401242144');

INSERT INTO book_subjects (book_id, subject)
SELECT id, 'Ingenieria de software' FROM books WHERE isbn IN
  ('9780134494166', '9780134757599');
