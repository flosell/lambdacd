# -*- mode: ruby -*-
# vi: set ft=ruby :

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.box = "hashicorp/precise64"

  config.vm.define :backend_ci do |backend_ci|
    backend_ci.vm.provision :shell, inline: 'sudo aptitude update && sudo aptitude install -y openjdk-7-jdk'
    backend_ci.vm.hostname = "backend-ci"
    backend_ci.vm.network "forwarded_port", guest: 8084, host: 18084
  end

  config.vm.define :frontend_ci do |frontend_ci|
    frontend_ci.vm.provision :shell, inline: 'sudo aptitude update && sudo aptitude install -y apache2 && sudo chown vagrant:vagrant /var/www'
    frontend_ci.vm.hostname = "frontend-ci"
    frontend_ci.vm.network "forwarded_port", guest: 80, host: 20080
  end

end
