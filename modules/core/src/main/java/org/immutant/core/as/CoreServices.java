/*
 * Copyright 2008-2013 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.immutant.core.as;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;

public class CoreServices {

    private CoreServices() {
    }

    public static final ServiceName IMMUTANT    = ServiceName.of( "immutant" );
    public static final ServiceName CORE        = IMMUTANT.append( "core" );
    public static final ServiceName HOUSEKEEPER = IMMUTANT.append( "housekeeper" );
    public static final ServiceName INJECTION   = CORE.append( "injection" );
    
    public static final ServiceName INJECTABLE_HANDLER_REGISTRY   = INJECTION.append( "injectable-handler-registry" );
    
    public static ServiceName serviceRegistryName(DeploymentUnit unit) {
        return unit.getServiceName().append( CORE ).append( "service-registry" );
    }
    
    public static ServiceName serviceTargetName(DeploymentUnit unit) {
        return unit.getServiceName().append( CORE ).append( "service-target" );
    }
    
    public static ServiceName runtimeInjectionAnalyzerName(DeploymentUnit unit) {
        return unit.getServiceName().append( INJECTION ).append( "runtime-injection-analyzer" );
    }
    
    public static ServiceName appNamespaceContextSelector(DeploymentUnit unit) {
        return unit.getServiceName().append( CORE ).append( "ns-context-selector" );
    }

    public static ServiceName housekeeper(DeploymentUnit unit) {
        return unit.getServiceName().append( HOUSEKEEPER );
    }
 
    public static ServiceName runtime(DeploymentUnit unit) {
        return unit.getServiceName().append( CORE ).append( "clojure-runtime" );
    }

    public static ServiceName appInitializer(DeploymentUnit unit) {
        return unit.getServiceName().append( CORE ).append( "application-initializer" );
    }
    
    public static ServiceName tmpResourceMounter(DeploymentUnit unit) {
        return unit.getServiceName().append( "resource-mounter" );
    }

}
