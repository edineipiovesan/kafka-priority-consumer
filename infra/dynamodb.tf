module "dynamodb_table" {
  source  = "terraform-aws-modules/dynamodb-table/aws"
  version = "3.2.0"


  name      = "offset-control"
  hash_key  = "topic"
  range_key = "partition"

  billing_mode   = "PROVISIONED"
  read_capacity  = 1
  write_capacity = 10

  attributes = [
    {
      name = "topic"
      type = "S"
    },
    {
      name = "partition"
      type = "N"
    }
  ]

  tags = {
    Terraform   = "true"
    Environment = "staging"
  }
}