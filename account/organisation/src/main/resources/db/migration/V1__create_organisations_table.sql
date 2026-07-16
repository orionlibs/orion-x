CREATE TABLE organisations
(
    user_id    UUID                     NOT NULL PRIMARY KEY,
    username   VARCHAR(255)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
