terraform {
    required_version = ">= 0.14"
}

data "azurerm_key_vault_secret" "postgres_user" {
  key_vault_id = var.app_config_key_vault_id
  name = "functionapp-postgres-user"
}

data "azurerm_key_vault_secret" "postgres_pass" {
  key_vault_id = var.app_config_key_vault_id
  name = "functionapp-postgres-pass"
}

resource "azurerm_postgresql_server" "postgres_server" {
  name = var.name
  location = var.location
  resource_group_name = var.resource_group
  administrator_login = data.azurerm_key_vault_secret.postgres_user.value
  administrator_login_password = data.azurerm_key_vault_secret.postgres_pass.value

  sku_name = "GP_Gen5_4"
  version = "11"
  storage_mb = 5120

  auto_grow_enabled = (var.environment == "prod" ? true : false)

  ssl_enforcement_enabled = true
  ssl_minimal_tls_version_enforced = "TLS1_2"

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    "environment" = var.environment
  }
}

resource "azurerm_postgresql_virtual_network_rule" "allow_public_subnet" {
  name = "AllowPublicSubnet"
  resource_group_name = var.resource_group
  server_name = azurerm_postgresql_server.postgres_server.name
  subnet_id = var.public_subnet_id
}

resource "azurerm_postgresql_virtual_network_rule" "allow_private_subnet" {
  name = "AllowPrivateSubnet"
  resource_group_name = var.resource_group
  server_name = azurerm_postgresql_server.postgres_server.name
  subnet_id = var.private_subnet_id
}

resource "azurerm_postgresql_database" "prime_data_hub_db" {
  name = "prime_data_hub"
  resource_group_name = var.resource_group
  server_name = azurerm_postgresql_server.postgres_server.name
  charset = "UTF8"
  collation = "English_United States.1252"

  lifecycle {
    prevent_destroy = true
  }
}

resource "azurerm_postgresql_database" "metabase_db" {
  count = (var.environment == "prod" ? 0 : 1)
  name = "metabase"
  resource_group_name = var.resource_group
  server_name = azurerm_postgresql_server.postgres_server.name
  charset = "UTF8"
  collation = "English_United States.1252"

  lifecycle {
    prevent_destroy = true
  }
}

output "server_name" {
  value = azurerm_postgresql_server.postgres_server.name
}

output "postgres_user" {
  value = data.azurerm_key_vault_secret.postgres_user.value
}

output "postgres_pass" {
  value = data.azurerm_key_vault_secret.postgres_pass.value
}
