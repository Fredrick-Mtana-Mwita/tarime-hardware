/**
 * Shows a delivery address line by line. A "GPS: lat,lng" line becomes an "Open in maps" link built here
 * from the numbers only, so text typed by a customer can never become an arbitrary link.
 */
export function AddressText({ value }: { value: string }) {
  return <span className="address-text">{value.split('\n').map((line, index) => {
    const gps = /^GPS: (-?\d{1,2}\.\d{1,6}),(-?\d{1,3}\.\d{1,6})\b/.exec(line)
    return <span key={index}>{gps ? <a className="text-link" href={`https://maps.google.com/?q=${gps[1]},${gps[2]}`} target="_blank" rel="noopener noreferrer">Open location in maps ({gps[1]}, {gps[2]})</a> : line}</span>
  })}</span>
}
