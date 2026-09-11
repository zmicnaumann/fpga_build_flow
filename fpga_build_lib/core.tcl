# ============================================================
# core.tcl
# Common utility functions for FPGA build flows.
# ============================================================

proc log_info {msg} {
    puts "INFO: $msg"
}

proc log_warn {msg} {
    puts "WARNING: $msg"
}

proc log_error {msg} {
    puts stderr "ERROR: $msg"
}

proc is_dry_run {} {
    global BUILD_MODE
    return [expr {$BUILD_MODE eq "dry_run"}]
}

proc is_vivado {} {
    global BUILD_MODE
    return [expr {$BUILD_MODE eq "vivado"}]
}

proc require_supported_build_mode {} {
    global BUILD_MODE

    set supported_modes {dry_run vivado}

    if {[lsearch -exact $supported_modes $BUILD_MODE] < 0} {
        error "Unsupported BUILD_MODE: $BUILD_MODE"
    }
}

proc require_file {path} {
    set path [file normalize $path]

    if {![file exists $path]} {
        error "Required file does not exist: $path"
    }

    if {![file isfile $path]} {
        error "Expected file but found something else: $path"
    }

    log_info "Found file: $path"

    return $path
}


proc require_dir {path} {
    set path [file normalize $path]

    if {![file exists $path]} {
        error "Required directory does not exist: $path"
    }

    if {![file isdirectory $path]} {
        error "Expected directory but found something else: $path"
    }

    return $path
}


proc ensure_dir {path} {
    if {![file exists $path]} {
        file mkdir $path
        log_info "Created directory: $path"
    }

    return [file normalize $path]
}