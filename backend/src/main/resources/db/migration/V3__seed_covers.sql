-- by Jeremy Posada
-- Portadas de covers.openlibrary.org, solo para los ISBN que si la tienen alla.
-- "La casa de los espiritus" se queda sin URL a proposito: muestra el respaldo tipografico del frontend.
UPDATE books SET cover_url = 'https://covers.openlibrary.org/b/isbn/' || isbn || '-L.jpg'
WHERE cover_url IS NULL
  AND isbn IN ('9788497592581', '9788437604572', '9780134494166', '9780134757599');
