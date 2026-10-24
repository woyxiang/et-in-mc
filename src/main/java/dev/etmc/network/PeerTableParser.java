package dev.etmc.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses `easytier-cli peer` output (v2.6.4 markdown table):
 * | ipv4 | hostname | cost | lat(ms) | loss | rx | tx | tunnel | NAT | version |
 * Self row has cost=Local; ipv4 carries a "/24" prefix length.
 */
public final class PeerTableParser {
    private PeerTableParser() { }

    public static List<PeerInfo> parse(String output) {
        List<PeerInfo> peers = new ArrayList<>();
        for (List<String> row : EasyTierCliRunner.parseTable(output)) {
            // data rows have 10 cells + leading empty cell from split("|")
            if (row.size() < 11) {
                continue;
            }
            String ipv4 = NodeInfo.stripPrefixLen(row.get(1));
            // header row and malformed rows: real rows start with an IPv4 address
            if (!ipv4.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                continue;
            }
            peers.add(new PeerInfo(
                    ipv4,
                    row.get(2),
                    row.get(3),
                    row.get(4),
                    row.get(5),
                    row.get(6),
                    row.get(7),
                    row.get(8),
                    row.get(9),
                    row.get(10)));
        }
        return peers;
    }
}
