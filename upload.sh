if [ $# -lt 1 ]; then
  echo "Error: At least 1 parameters are required."
  echo "Usage: $0 [s3-uri]"
  exit 1
fi

aws s3 cp build/distributions $1 --recursive --exclude "*" --include "*.zip"
aws s3 cp deployment/layer.zip $1