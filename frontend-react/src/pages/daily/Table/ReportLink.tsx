import download from "downloadjs";
import { Button } from "@trussworks/react-uswds";
import { useOktaAuth } from "@okta/okta-react";

import AuthResource from "../../../resources/AuthResource";
import ReportResource from "../../../resources/ReportResource";
import { GLOBAL_STORAGE_KEYS } from "../../../components/GlobalContextProvider";

interface Props {
    /* REQURIED
    A ReportResource is passed in using this property. This is necessary for download()
    since that function relies on the content, fileName, and mimeType properties */
    report: ReportResource | undefined;

    /* OPTIONAL
    This boolean flag changes the return value from a standart <a> link to a <Button> (USWDS)
    so this single component can be used in Daily.tsx and Details.tsx */
    button?: boolean;
}

const formatFileType = (fileType: string) => {
    if (fileType === "HL7_BATCH") return "HL7(BATCH)";
    return fileType;
};

/* 
    This element provides a download link on each row of the table and on the report
    details page
*/
function ReportLink(props: Props) {
    const { authState } = useOktaAuth();
    const organization = localStorage.getItem(GLOBAL_STORAGE_KEYS.GLOBAL_ORG);

    const handleClick = (e: any) => {
        e.preventDefault();
        if (props.report !== undefined && props.report.reportId !== undefined) {
            let reportId = props.report.reportId;
            fetch(
                `${AuthResource.getBaseUrl()}/api/history/report/${reportId}`,
                {
                    headers: {
                        Authorization: `Bearer ${authState?.accessToken?.accessToken}`,
                        Organization: organization!,
                    },
                }
            )
                .then((res) => res.json())
                .then((report) => {
                    // The filename to use for the download should not contain blob folders if present
                    let filename = decodeURIComponent(report.fileName);
                    let filenameStartIndex = filename.lastIndexOf("/");
                    if (
                        filenameStartIndex >= 0 &&
                        filename.length > filenameStartIndex + 1
                    )
                        filename = filename.substring(filenameStartIndex + 1);
                    download(report.content, filename, report.mimetype);
                })
                .catch((error) => console.log(error));
        }
    };

    if (!props.button) {
        return (
            <a href="/" onClick={handleClick} className="usa-link">
                {props.report !== undefined
                    ? formatFileType(props.report.fileType)
                    : ""}
            </a>
        );
    } else {
        return (
            <Button
                type="button"
                outline
                onClick={handleClick}
                className="usa-button usa-button--outline float-right"
            >
                {props.report !== undefined
                    ? formatFileType(props.report.fileType)
                    : ""}
            </Button>
        );
    }
}

export default ReportLink;
