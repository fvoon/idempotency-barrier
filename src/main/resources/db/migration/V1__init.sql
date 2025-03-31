create table if not exists transfer_steps (
    id varchar(255) primary key,
    amount int,
    updated_at timestamp,
    status varchar(50),
    version int default 0,
    locked_until timestamp
)