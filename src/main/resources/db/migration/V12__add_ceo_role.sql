-- CEO: o dono do produto, que acompanha o painel de métricas e saúde. Não é da equipe do órgão público
ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('OPERATOR', 'ADMIN', 'CEO'));
