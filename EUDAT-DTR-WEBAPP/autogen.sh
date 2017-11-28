#!/bin/bash

declare -a pkg_deps=(
    "dorepository-api-1.1.jar"
    "tika-app-1.11-no-logging-no-http.jar"
)

declare -a lib_deps=(
    "cnriutil.jar"
    "doapps.jar"
    "do.jar"
    "handle.jar"
    "javax.mail.jar"
    "je-3.3.98.jar"
    "jython-2.2.1.jar"
    "knowbots.jar"
)

# Root Git repo and EUDAT-DTR path 
root_git_dir=$(git rev-parse --show-toplevel)
eudat_dtr_path="$root_git_dir/EUDAT-DTR-WEBAPP"

# paths to fetch dependencies from
deps_pkg="cordra/data/webapps-priority/ROOT.war"
deps_lib="cordra/sw/lib"
deps_pkg_path="$root_git_dir/$deps_pkg"
deps_lib_path="$root_git_dir/$deps_lib"

# paths to deploy dependencies from
build_deps_path="$eudat_dtr_path/build-deps"
webinf_lib_path="$eudat_dtr_path/WebContent/WEB-INF/lib"
webinf_classes_path="$eudat_dtr_path/WebContent/WEB-INF/classes"

# check that the script is run from the repository's root directory
if [[ ! "$PWD" == "$eudat_dtr_path" ]];
then
    echo "Please run the script from the EUDAT-DTR-WEBAPP's root directory."
    exit 1
fi

# check that ROOT.war exists
if [[ ! -e "$deps_pkg_path" ]]; 
then
    echo "The ROOT.war package containing the dependencies was not found." 
    echo "Aborting."
    exit 1
fi

# create build-deps directory if it doesn't exist
if [[ ! -e "$build_deps_path" ]];
then
    mkdir -p ${build_deps_path}
fi

# extract Cordra specific dependencies from ROOT.war
echo "*** Extracting Cordra-specific dependencies into $build_deps_path ..."

for dep in "${pkg_deps[@]}"
do
    echo "- ${dep}:"
    unzip -jo -d ${build_deps_path} ${deps_pkg_path} "WEB-INF/lib/${dep}"
done

if [[ $? == 0 ]]; then
    echo "Dependency files successfully extracted to $webinf_lib_path"
else
    echo "An error happened when extracting dependencies to $webinf_lib_path"
    exit 1
fi

# ROOT.war does not include all dependencies required, we need
# some from Cordra's 'lib' directory
echo ""
echo "*** Copying additional Cordra-specific dependencies into $deps_lib_path ..."

error=0
for dep in "${lib_deps[@]}"
do
    echo "- ${dep}:"
    echo "    cp ${deps_lib_path}/${dep} -> ${build_deps_path}/${dep}"
    cp "${deps_lib_path}/${dep}" ${build_deps_path}

    if [[ $? != 0 ]]; then
        error=1
        break
    fi
done

if [[ $error == 0 ]]; then
    echo ""
    echo "All dependencies copied."
    echo "You may now build the webapp by running 'gradle-assemble'." 
else
    echo ""
    echo "An error happened when copying dependencies to $build_deps_path"
    exit 1
fi

exit 0
