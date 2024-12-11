echo "Current directory: $(pwd)"

gradle build
cd build/distributions
rm -rf kvs_trigger overlay_audio process_contact ws_on_connect ws_on_disconnect ws_send_message ws_utils

nodejs_files=("kvs_trigger" "process_contact")
python_files=("overlay_audio" "ws_on_connect" "ws_on_disconnect" "ws_send_message" "ws_utils")

for file in ${nodejs_files[@]}
do
    echo "Processing file: $file"
    cp -R ../../functions/$file .
    cd $file
    npm install aws-sdk
    zip -r9 ../$file.zip .
    cd ..
done

for file in ${python_files[@]}
do
    echo "Processing file: $file"
    cp -R ../../functions/$file .
    cd $file
    zip -r9 ../$file.zip .
    cd ..
done
