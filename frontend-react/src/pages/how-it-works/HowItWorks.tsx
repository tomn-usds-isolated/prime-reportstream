import { Route, Switch, useRouteMatch } from "react-router-dom";
import { SecurityPractices } from "./SecurityPractices";
import { ELRChecklist } from "./ElrChecklist";
import { GettingStarted } from "./GettingStarted";
import { WhereWereLive } from "./WhereWereLive";
import { WebReceiverGuide } from "./WebReceiverGuide";
import { SystemsAndSettings } from "./SystemsAndSettings";
import HIWNavigation from "./HIWNavigation";

export const HowItWorks = () => {
    let { path } = useRouteMatch();

    return (
        <section className="grid-container margin-bottom-5">
            <div className="grid-row grid-gap">
                <HIWNavigation />
                <div className="tablet:grid-col-8 usa-prose rs-documentation">
                    <Switch>
                        <Route
                            path={`${path}/getting-started`}
                            component={GettingStarted}
                        />
                        <Route
                            path={`${path}/elr-checklist`}
                            component={ELRChecklist}
                        />
                        <Route
                            path={`${path}/data-download-guide`}
                            component={WebReceiverGuide}
                        />
                        <Route
                            path={`${path}/where-were-live`}
                            component={WhereWereLive}
                        />
                        <Route
                            path={`${path}/systems-and-settings`}
                            component={SystemsAndSettings}
                        />
                        <Route
                            path={`${path}/security-practices`}
                            component={SecurityPractices}
                        />
                    </Switch>
                </div>
            </div>
        </section>
    );
};
