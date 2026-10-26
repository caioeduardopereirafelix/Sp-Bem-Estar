-- Administrador: cadastra, desativa e reativa operadores. Desativar alguém ou trocar a senha invalida os
-- tokens já emitidos (tokens_valid_after), que de outro jeito valeriam até vencer (8 h).

ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('OPERATOR', 'ADMIN'));

ALTER TABLE app_user ADD COLUMN tokens_valid_after TIMESTAMPTZ; -- tokens emitidos antes disso são recusados

-- O primeiro usuário, criado pelo OPERATOR_EMAIL na instalação, vira o administrador
UPDATE app_user SET role = 'ADMIN'
WHERE id = (SELECT min(id) FROM app_user)
  AND NOT EXISTS (SELECT 1 FROM app_user WHERE role = 'ADMIN');
