#!/bin/bash

while true; do
  docker_id="$(docker ps | grep cp-server | awk '{print $1}' | shuf | head -n 1)"
  echo "restarting $docker_id"
  docker restart $docker_id
  echo "restarted $docker_id"
  sleep 10
done

