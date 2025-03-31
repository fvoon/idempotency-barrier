# Idempotency Barrier

A proof-of-concept implementation of an idempotency barrier using a relational database (PostgreSQL) to coordinate 
execution in a distributed system. This repo demonstrates how to enforce at-most-once execution semantics using 
optimistic locking and versioning to achieve SQL row-level locking.

## 🧠 Concept

This implementation shows how to use a database as a distributed lock and barrier mechanism for idempotency. It ensures 
that only one execution for a given key proceeds at a time — others are either blocked or skipped. Retries are allowed
after the lock expires and the lock timeouts are configurable.

This is especially useful in systems where retrying the same operation multiple times (e.g., processing a webhook or 
job) may result in inconsistent side effects.