--! qt:dataset:src
DESCRIBE FUNCTION stack;

EXPLAIN SELECT x, y FROM src LATERAL VIEW STACK(2, 'x', array(1), 'z') a AS x, y LIMIT 2;
EXPLAIN SELECT x, y FROM src LATERAL VIEW STACK(2, 'x', array(1), 'z', array(4)) a AS x, y LIMIT 2;

SELECT x, y FROM src LATERAL VIEW STACK(2, 'x', array(1), 'z') a AS x, y LIMIT 2;
SELECT x, y FROM src LATERAL VIEW STACK(2, 'x', array(1), 'z', array(4)) a AS x, y LIMIT 2;

EXPLAIN
SELECT stack(1, "en", "dbpedia", NULL );
SELECT stack(1, "en", "dbpedia", NULL );

EXPLAIN SELECT STACK(2, 'a', 'b', 'c', 'd', 'e');
SELECT STACK(2, 'a', 'b', 'c', 'd', 'e');
