import { SideNav } from '@trussworks/react-uswds';
import { useRouteMatch } from 'react-router';
import { NavLink } from 'react-router-dom';

function HIWNavigation() {
    let { url } = useRouteMatch();

    var itemsMenu = [
        <NavLink
            to={`${url}/getting-started`}
            activeClassName="usa-current"
            className="usa-nav__link"
        >
            Getting started
        </NavLink>,
        <NavLink
            to={`${url}/elr-checklist`}
            activeClassName="usa-current"
            className="usa-nav__link"
        >
            ELR onboarding checklist
        </NavLink>,
        <NavLink
            to={`${url}/data-download-guide`}
            activeClassName="usa-current"
            className="usa-nav__link"
        >
            Data download website guide
        </NavLink>,
        <NavLink
            to={`${url}/where-were-live`}
            activeClassName="usa-current"
            className="usa-nav__link"
        >
            Where we're live
        </NavLink>,
        <NavLink
            to={`${url}/systems-and-settings`}
            activeClassName="usa-current"
            className="usa-nav__link"
        >
            Systems and settings
        </NavLink>,
        <NavLink
            to={`${url}/security-practices`}
            activeClassName="usa-current"
            className="usa-nav__link"
        >
            Security practices
        </NavLink>,
    ];
    return (
        <div className="tablet:grid-col-4">
            <SideNav items={itemsMenu} />
        </div>
    )
}

export default HIWNavigation
