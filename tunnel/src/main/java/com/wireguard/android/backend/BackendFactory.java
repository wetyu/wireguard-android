/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.content.Context;

import com.wireguard.android.backend.GoBackend.AlwaysOnCallback;
import com.wireguard.android.util.ModuleLoader;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.annotation.Nullable;

import androidx.annotation.IntDef;

public final class BackendFactory {
    private BackendFactory() {
    }

    public static final int DISABLE_KERNEL_BACKEND = 1 << 0;
    public static final int DISABLE_GO_BACKEND = 1 << 1;
    public static final int DISABLE_LOAD_KERNEL_MODULE = 1 << 2;
    public static final int DISABLE_MULTIPLE_TUNNELS = 1 << 3;

    @IntDef(
            flag = true,
            value={
                    DISABLE_KERNEL_BACKEND,
                    DISABLE_GO_BACKEND,
                    DISABLE_LOAD_KERNEL_MODULE,
                    DISABLE_MULTIPLE_TUNNELS
            }
    )
    @Retention(RetentionPolicy.SOURCE)
    public @interface BackendFactoryFlags { }

    public static Backend make(@BackendFactoryFlags final int flags, final Context context, @Nullable final RootShell rootShell, @Nullable final ToolsInstaller toolsInstaller, @Nullable final ModuleLoader moduleLoader, @Nullable final AlwaysOnCallback alwaysOnCallback) {
        Backend backend = null;
        boolean didStartRootShell = false;

        if (rootShell == null && (flags & DISABLE_KERNEL_BACKEND) == 0)
            throw new IllegalArgumentException("The kernel backend requires a RootShell object");
        if (toolsInstaller == null && (flags & DISABLE_KERNEL_BACKEND) == 0)
            throw new IllegalArgumentException("The kernel backend requires a ToolsInstaller object");
        if (moduleLoader == null && (flags & (DISABLE_LOAD_KERNEL_MODULE | DISABLE_KERNEL_BACKEND)) == 0)
            throw new IllegalArgumentException("Kernel module loading requires a ModuleLoader object");

        if ((flags & (DISABLE_KERNEL_BACKEND | DISABLE_LOAD_KERNEL_MODULE)) == 0) {
            if (!ModuleLoader.isModuleLoaded() && moduleLoader.moduleMightExist()) {
                try {
                    rootShell.start();
                    didStartRootShell = true;
                    moduleLoader.loadModule();
                } catch (final Exception ignored) {
                }
            }
        }

        if ((flags & DISABLE_KERNEL_BACKEND) == 0 && ModuleLoader.isModuleLoaded()) {
            try {
                if (!didStartRootShell)
                    rootShell.start();
                final WgQuickBackend wgQuickBackend = new WgQuickBackend(context, rootShell, toolsInstaller);
                wgQuickBackend.setMultipleTunnels((flags & DISABLE_MULTIPLE_TUNNELS) == 0);
                backend = wgQuickBackend;
            } catch (final Exception ignored) {
            }
        }

        if ((flags & DISABLE_GO_BACKEND) == 0 && backend == null) {
            backend = new GoBackend(context);
            if (alwaysOnCallback != null)
                GoBackend.setAlwaysOnCallback(alwaysOnCallback);
        }
        return backend;
    }

    public static Backend make(@BackendFactoryFlags final int flags, final Context context, final RootShell rootShell, final ModuleLoader moduleLoader, final ToolsInstaller toolsInstaller) {
        return make(flags, context, rootShell, toolsInstaller, moduleLoader, null);
    }

    public static Backend make(@BackendFactoryFlags final int flags, final Context context, final RootShell rootShell, final ToolsInstaller toolsInstaller) {
        return make(flags | DISABLE_LOAD_KERNEL_MODULE, context, rootShell, toolsInstaller, null, null);
    }

    public static Backend make(@BackendFactoryFlags final int flags, final Context context, final AlwaysOnCallback alwaysOnCallback) {
        return make(flags | DISABLE_KERNEL_BACKEND, context, null, null, null, alwaysOnCallback);
    }

    public static Backend make(@BackendFactoryFlags final int flags, final Context context) {
        return make(flags | DISABLE_KERNEL_BACKEND, context, null, null, null, null);
    }
}
