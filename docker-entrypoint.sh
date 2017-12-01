#!/usr/bin/env bash
#set -x

async_run() {

    trap 'kill -TERM $PID' INT TERM

    $@ &
    PID=$!

    while kill -0 $PID > /dev/null 2>&1; do 
        wait $PID; 
    done

    # give time for output to reach terminal
    sleep 5

    return $EXIT_STATUS
}

configure_cordra() {
    # the user may not want to configure the repository if they only 
    # want to test the software, or if they are already happy with
    # the current configuration
    while true; do
        read -p "Do you wish to configure the EUDAT-DTR's repository? " yn 
        case $yn in
            [Yy]* ) 
                eval "./configure-unconfigured-cordra"
                break
                ;;
            [Nn]* ) 
                break;;
            * ) echo "Please answer yes or no.";;
        esac
    done
}

configure_b2access() {
    # the user may not want to configure b2access support at this moment
    while true; do
        read -p "Do you wish to configure the support for B2ACCESS authentication? " yn 
        case $yn in
            [Yy]* ) 
                #eval "./configure-unconfigured-cordra"
                break
                ;;
            [Nn]* ) 
                break;;
            * ) echo "Please answer yes or no.";;
        esac
    done
}

# if the 'configure' argument was passed to docker,
# configure this DTR instance
if [ "$1" = "configure" ]; then

    # make sure that 'configure' was called with docker in interactive mode
    if [ ! -t 0 ]; then
        echo "ERROR: configuration must be run in an interactive shell!"
        echo "   (try: docker run -v CFG_FILES_PATH:CONTAINER_PATH -it DOCKER_TAG configure)"_
        exit 1
    fi

    configure_cordra
    configure_b2access
    exit 0

elif [ "$1" = "./startup" ]; then

    if [ ! -e $DTRDATA/privatekey ]; then
        echo "This image of EUDAT-DTR has not been configured"
        echo "   (try: docker run -v CFG_FILES_PATH:CONTAINER_PATH -it DOCKER_TAG configure)"_
        exit 1
    fi

    async_run $@
else
    exec $@
fi

echo "done!"
