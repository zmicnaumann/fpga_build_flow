# ============================================================
# sources.tcl
# ============================================================

# Checked-in RTL — must exist even during dry run
add_systemverilog "$RTL_DIR/right.sv"
add_systemverilog "$RTL_DIR/foo.sv"

# Generated sources — don't exist during dry run
add_bd "$BUILD_DIR/${BD_NAME}/${BD_NAME}.bd"

add_generated_verilog \
    "$BUILD_DIR/${BD_NAME}/hdl/${BD_NAME}_wrapper.v"