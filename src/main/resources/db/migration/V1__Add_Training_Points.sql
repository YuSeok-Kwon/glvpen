-- 훈련 포인트 컬럼 추가
ALTER TABLE custom_players
ADD COLUMN training_points INT DEFAULT 0 AFTER experience;

-- 기존 선수들에게 레벨에 따른 훈련 포인트 부여
UPDATE custom_players
SET training_points = (level - 1) * 5
WHERE mode = 'RPG' AND training_points IS NULL;
