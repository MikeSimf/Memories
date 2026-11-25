-- Звания героя: произвольные текстовые титулы, их может быть несколько.
CREATE TABLE hero_titles (
    hero_id UUID NOT NULL REFERENCES heroes(id) ON DELETE CASCADE,
    position INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    PRIMARY KEY (hero_id, position)
);

CREATE INDEX idx_hero_titles_hero ON hero_titles(hero_id);
