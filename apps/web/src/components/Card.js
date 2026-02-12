import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
export function Card({ title, subtitle, children }) {
    return (_jsxs("section", { className: "card", children: [_jsx("h2", { children: title }), subtitle ? _jsx("p", { className: "muted", children: subtitle }) : null, _jsx("div", { children: children })] }));
}
