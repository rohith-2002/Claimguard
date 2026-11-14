import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";

const mockAlert = {
  id: 1,
  claimId: "CLM-101",
  policyNumber: "POL-101",
  claimantMobile: "9876543210",
  score: 85,
  status: "OPEN",
  lineOfBusiness: "MOTOR",
  triggeredRules: [
    { code: "EARLY_CLAIM", reason: "Loss occurred 6 days after policy start (limit 30)." }
  ]
};

describe("investigator queue", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ items: [mockAlert], totalElements: 1, totalPages: 1, number: 0, size: 10 })
      })
      .mockResolvedValueOnce({ ok: true, json: async () => ({}) })
      .mockResolvedValue({
        ok: true,
        json: async () => ({ items: [{ ...mockAlert, claimantMobile: "******3210", status: "CONFIRMED_FRAUD" }], totalElements: 1, totalPages: 1, number: 0, size: 10 })
      })
    );
  });

  it("shows the queue with a masked mobile number and rule details", async () => {
    render(<App />);
    expect(await screen.findByText("CLM-101")).toBeInTheDocument();
    expect(screen.getByText("******3210")).toBeInTheDocument();
    expect(screen.queryByText("9876543210")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "View" }));
    expect(await screen.findByText("Loss occurred 6 days after policy start (limit 30).")).toBeInTheDocument();
  });

  it("sends a disposition with the required note and investigator", async () => {
    render(<App />);
    await screen.findByText("CLM-101");
    fireEvent.click(screen.getByRole("button", { name: "View" }));

    fireEvent.change(screen.getByLabelText("Investigator"), { target: { value: "Alex" } });
    fireEvent.change(screen.getByLabelText(/Note/), { target: { value: "Evidence reviewed" } });
    fireEvent.click(screen.getByRole("button", { name: "Save decision" }));

    await waitFor(() => {
      const calls = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls;
      const patchCall = calls.find((call) => call[1]?.method === "PATCH");
      expect(patchCall).toBeDefined();
      expect(JSON.parse(patchCall[1].body)).toEqual({
        status: "CONFIRMED_FRAUD",
        note: "Evidence reviewed",
        investigator: "Alex"
      });
    });
  });
});