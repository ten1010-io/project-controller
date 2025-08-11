#!/usr/bin/env bash
script_path=$(dirname "$0")
output_dir_path="$script_path/output"

if [ ! -e "$output_dir_path/ca.crt" ]; then
  $script_path/create-ca-crt.sh
fi
if [ ! -e "$output_dir_path/tls.p12" ]; then
  $script_path/create-tls-crt.sh
fi
ca_bundle=$($script_path/get-ca-bundle.sh)
sed -i 's/caBundle:.*/''caBundle: '"$ca_bundle"'/g' "../project-controller/patches.yaml"
cp -f $output_dir_path/tls.p12 $script_path/../
