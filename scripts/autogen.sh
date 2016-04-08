#!/bin/bash

deps_pkg="cordra/data/webapps-priority/ROOT.war"
deps_lib="cordra/sw/lib/"
root_git_dir=$(git rev-parse --show-toplevel)

deps_pkg_path="$root_git_dir/$deps_pkg"
deps_lib_path="$root_git_dir/$deps_lib"
deps_dest_path="$root_git_dir/cordra/sw/eudat-dtr/build-deps"

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
unzip -jo -d $deps_dest_path $deps_pkg_path 'WEB-INF/lib/*.jar'

if [[ $? == 0 ]]; then
    echo "Dependency files successfully extracted to $deps_dest_path"
else
    echo "An error happened when extracting dependencies to $deps_dest_path"
    exit 1
fi

echo "Copying additional dependencies in $deps_lib_path ..."
cp $deps_lib_path/*.jar $deps_dest_path

echo "All dependencies copied."
echo "You may now build the package by navigating to the cordra/sw/eudat-dtr"`
    `"directory and running 'ant war'."

exit 0
