export function getPowerMode() {
    if (typeof navigator !== "undefined" && "connection" in navigator) {
        const connection = navigator.connection;
        if (connection?.saveData)
            return "low_power";
    }
    return "normal";
}
