async function fetchReports() {
    let config = { headers: {  'Authorization': `Bearer ${window.jwt}`} }   
    let baseURL = window.location.origin.includes( "localhost") ? "http://localhost:7071" :
    window.location.origin.includes( "staging" ) ? "https://staging.prime.cdc.gov" :
    "https://prime.cdc.gov";     
    return await axios.get(`${baseURL}/api/history/report`, config).then( res => res.data );
};

