CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000) NOT NULL,
    system_prompt VARCHAR(4000) NOT NULL,
    tool_names VARCHAR(255) NOT NULL,
    planning_prompt VARCHAR(4000)
);
