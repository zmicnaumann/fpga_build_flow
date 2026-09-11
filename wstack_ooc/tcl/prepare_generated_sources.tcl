# Generate BD + HDL wrapper

set BD_NAME "right_bd"
set BD_TCL  "$ROOT_DIR/bd/${BD_NAME}.tcl"

require_file $BD_TCL

generate_bd $BD_TCL
generate_bd_wrapper $BD_NAME