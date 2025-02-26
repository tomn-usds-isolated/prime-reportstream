import DOMPurify from "dompurify";

import { SectionProp } from "../HomeProps";
import usamapsvg from "../../../content/usa_w_territories.svg"; // in /content dir to get unique filename per build

export default function LiveMapSection({ section }: { section: SectionProp }) {
    let cleanDescriptionHtml = DOMPurify.sanitize(section!.description!);
    return (
        <div className="tablet:margin-bottom-8">
            <h2
                data-testid="heading"
                className="font-sans-lg tablet:font-sans-xl margin-top-0 tablet:margin-bottom-0"
            >
                {section.title}
            </h2>
            <p
                data-testid="summary"
                className="usa-intro margin-top-1 text-base"
            >
                {section.summary}
            </p>
            <div data-testid="map">
                <a href="/how-it-works/where-were-live">
                    <img
                        src={usamapsvg}
                        alt="Map of states using ReportStream"
                    />
                </a>
            </div>
            <p
                data-testid="description"
                className="usa-prose margin-top-6"
                dangerouslySetInnerHTML={{ __html: cleanDescriptionHtml }}
            ></p>
        </div>
    );
}
