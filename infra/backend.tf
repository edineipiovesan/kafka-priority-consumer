terraform {
  backend "s3" {
    bucket         = "edn.tf-state"
    key            = "terraform/state"
    region         = "us-east-1"
    dynamodb_table = "edn.tf-lock"
  }
}