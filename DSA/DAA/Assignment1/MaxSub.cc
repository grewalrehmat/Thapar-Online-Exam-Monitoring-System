#include <iostream>
#include <vector>

using namespace std;

int crossSum(vector<int>& nums, int l, int m, int r) {
    int leftSum = INT_MIN, sum = 0;
    for (int i = m; i >= l; i--) {
        sum += nums[i];
        leftSum = max(leftSum, sum);
    }

    int rightSum = INT_MIN;
    sum = 0;
    for (int i = m + 1; i <= r; i++) {
        sum += nums[i];
        rightSum = max(rightSum, sum);
    }

    return leftSum + rightSum;
}

int helper(vector<int>& nums, int l, int r) {
    if (l == r) return nums[l];

    int m = l + (r - l) / 2;

    int left = helper(nums, l, m);
    int right = helper(nums, m + 1, r);
    int cross = crossSum(nums, l, m, r);

    return max({left, right, cross});
}

int maxSubArray(vector<int>& nums) {
    return helper(nums, 0, nums.size() - 1);
}


int main(){
    vector<int> nums = {-2, -5, 6, -2, -3, 1, 5, -6};
    int ans = maxSubArray(nums);
    cout<<ans;
}