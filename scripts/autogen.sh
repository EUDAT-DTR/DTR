#!/bin/bash

# Root Git repo and EUDAT-DTR path 
root_git_dir=$(git rev-parse --show-toplevel)
eudat_dtr_path="$root_git_dir/cordra/sw/eudat-dtr"

# paths to fetch dependencies from
deps_pkg="cordra/data/webapps-priority/ROOT.war"
deps_lib="cordra/sw/lib/"
deps_pkg_path="$root_git_dir/$deps_pkg"
deps_lib_path="$root_git_dir/$deps_lib"

# paths to deploy dependencies from
build_deps_path="$eudat_dtr_path/build-deps"
webinf_lib_path="$eudat_dtr_path/WebContent/WEB-INF/lib"
webinf_classes_path="$eudat_dtr_path/WebContent/WEB-INF/classes"

# check that the script is run from the repository's root directory
if [[ ! "$PWD" == "$root_git_dir" ]];
then
    echo "Please run the script from the repository's root directory."
    exit 1
fi

# check that ROOT.war exists
if [[ ! -e "$deps_pkg_path" ]]; 
then
    echo "The ROOT.war package containing the dependencies was not found." 
    echo "Aborting."
    exit 1
fi

echo "Extracting all .jar dependencies in $deps_pkg_path ..."

# extract dependencies from ROOT.war and copy them to 'build-deps'
unzip -jo -d $build_deps_path $deps_pkg_path 'WEB-INF/lib/*.jar'

if [[ $? == 0 ]]; then
    echo "Dependency files successfully extracted to $build_deps_path"
else
    echo "An error happened when extracting dependencies to $build_deps_path"
    exit 1
fi

# extract dependencies from ROOT.war and copy them to 'WebContent/WEB-INF/lib'
unzip -jo -d $webinf_lib_path $deps_pkg_path 'WEB-INF/lib/*.jar'

if [[ $? == 0 ]]; then
    echo "Dependency files successfully extracted to $webinf_lib_path"
else
    echo "An error happened when extracting dependencies to $webinf_lib_path"
    exit 1
fi

# extract classes from ROOT.war and copy them to 'WebContent/WEB-INF/classes'
unzip -jo -d $webinf_lib_path $deps_pkg_path 'WEB-INF/classes/'

if [[ $? == 0 ]]; then
    echo "Dependency files successfully extracted to $webinf_classes_path"
else
    echo "An error happened when extracting dependencies to $webinf_classes_path"
    exit 1
fi

# copy the additional dependencies found in 'cordra/sw/lib' to 'build-deps'
echo "Copying additional dependencies in $deps_lib_path ..."
cp $deps_lib_path/*.jar $build_deps_path

if [[ $? == 0 ]]; then
    echo "All dependencies copied."
    echo "You may now build the package by navigating to the "` 
        `"cordra/sw/eudat-dtr directory and running 'ant war'."
else
    echo "An error happened when copying dependencies to $build_deps_path"
    exit 1
fi

exit 0
