#!/usr/bin/env bash

function usage() {
    echo "usage: ${0} [OPTION]"
    echo ""
    echo "Runs the Terraform formatter on top of your code. The default mode (i.e. without any options) scans"
    echo "the files in the /operations directory."
    echo ""
    echo "Options:"
    echo "    --fix        Fixes and formats all Terraform files with \`terraform fmt\` in the /operations directory"
    echo "    --help|-h    Shows this help and exits successfully"
    echo ""
    echo "Examples:"
    echo ""
    echo "  $ ${0}"
    echo "      Checks all Terraform files in /operations with \`terraform fmt\`, returning zero if files are formatted"
    echo ""
    echo "  $ ${0} --fix"
    echo "      Fixes formatting of all Terraform files in /operations with \`terraform fmt\`"
    echo ""
    echo ""
}

function error() {
    echo "terraform-fmt> ERROR: ${*}"
}

function warning() {
    echo "terraform-fmt> Warning: ${*}"
}

function note() {
    echo "terraform-fmt> info: ${*}"
}

REPO_ROOT=$(git rev-parse --show-toplevel)
LOGFILE="terraform-fmt.log"

function terraform_fmt_check() {
    note "Checking Terraform formatting."
    MODIFIED_TF_FILES_COUNT=$(git status --porcelain | grep "\.tf$" | wc -l)
    RC=0
    if [[ ${MODIFIED_TF_FILES_COUNT?} != 0 ]]; then
        make -C "${REPO_ROOT?}/operations" -f "${REPO_ROOT?}/operations/Makefile" tf-cmd TF_CMD="terraform fmt -check -recursive /app/src" >"${REPO_ROOT?}/${LOGFILE?}" 2>&1
        RC=$?
    else
        note "Skipping this check, you made no changes to Terraform files..."
        RC=0
    fi

    return ${RC?}
}

function terraform_fmt_fix() {
    warning "Formatting all Terraform files."
    make -C "${REPO_ROOT?}/operations" -f "${REPO_ROOT?}/operations/Makefile" tf-cmd TF_CMD="terraform fmt -recursive /app/src" >"${REPO_ROOT?}/${LOGFILE?}" 2>&1
    return $?
}

# Parse arguments
HAS_UNRECOGNIZED=0

# Default type of thing we do
SELECTED_RUNMODE=""
RUNMODE_CHECK="check"
RUNMODE_FIX="fix"
while [[ ! -z "${1}" ]]; do
    case "${1}" in
    "--${RUNMODE_FIX?}")
        if [[ ! -z "${SELECTED_RUNMODE?}" ]]; then
            warning "The previously specified run-mode '${SELECTED_RUNMODE?}' will be overridden by the latest run-mode '${1:2}'."
        fi
        SELECTED_RUNMODE="${RUNMODE_FIX?}"
        ;;
    "--help" | "-h")
        usage
        exit 0
        ;;
    *)
        # Keep collecting the unrecognized options
        error "Option \"${1}\" is not a recognized option."
        HAS_UNRECOGNIZED=1
        ;;
    esac

    shift
done

# Exit in error if you provided any invalid option
if [[ ${HAS_UNRECOGNIZED?} != 0 ]]; then
    echo ""
    usage
    exit 1
fi

# Set default run-mode if none was select
if [[ -z "${SELECTED_RUNMODE?}" ]]; then
    SELECTED_RUNMODE="${RUNMODE_CHECK?}"
fi

RC=1 # Nothing done, fail
case "${SELECTED_RUNMODE?}" in
"${RUNMODE_CHECK?}")
    terraform_fmt_check
    RC=$?
    if [[ ${RC?} != 0 ]]; then
        error "(return code=${RC?}) Your Terraform files are not formatted."
        error "Run \`terraform fmt -recursive\` or \`${0} --fix\` to comply with formatting requirements."
    fi
    ;;
"${RUNMODE_FIX?}")
    # Default action
    terraform_fmt_fix
    RC=$?
    if [[ ${RC?} != 0 ]]; then
        error "(return code=${RC?}) Problem formatting Terraform files."
    fi
    ;;
*)
    error "The selected run-mode \"${SELECTED_RUNMODE?}\" is not a recognized one."
    exit 1
    ;;
esac

if [[ ${RC?} != 0 ]]; then
    error "Additional information can be found in the following files"
    error "     - ${REPO_ROOT?}/${LOGFILE?}"
fi

exit ${RC?}
